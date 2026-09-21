"""Seleciona por validação, calibra, avalia teste e exporta TFLite."""
import argparse,itertools,json,shutil,tempfile,time
from pathlib import Path
import numpy as np
from scipy.optimize import minimize_scalar
from sklearn.metrics import accuracy_score,f1_score,log_loss,classification_report,confusion_matrix
from leafcare.common import load_config,location,read_json,write_json,sha256,classes_hash
from leafcare.dataset import load_manifest
from leafcare.preprocessing import preprocess

def metrics(y,p):
 pred=p.argmax(1)
 return {"accuracy":float(accuracy_score(y,pred)),"macro_f1":float(f1_score(y,pred,average="macro",zero_division=0)),"top3_accuracy":float(np.mean([v in np.argsort(-q)[:3] for v,q in zip(y,p)])),"nll":float(log_loss(y,p,labels=range(p.shape[1])))}

def predict(model,c,rows):
 import tensorflow as tf
 size=c["image_size"]
 def gen():
  for r in rows:yield preprocess(location(c,"dataset_dir")/r["path"],size)[0]
 ds=tf.data.Dataset.from_generator(gen,output_signature=tf.TensorSpec((size,size,3),tf.float32)).batch(c["batch_size"]).prefetch(1)
 return model.predict(ds,verbose=0)

def temp_scale(p,t):
 z=np.log(np.clip(p,1e-7,1))/t;z-=z.max(1,keepdims=True);z=np.exp(z);return z/z.sum(1,keepdims=True)

def select_threshold(p,y):
 pred=p.argmax(1);conf=p.max(1);rows=[]
 for t in np.arange(.45,.951,.01):
  ok=conf>=t
  if ok.sum()>=10:rows.append({"threshold":round(float(t),2),"coverage":float(ok.mean()),"accepted_accuracy":float((pred[ok]==y[ok]).mean()),"accepted":int(ok.sum())})
 eligible=[r for r in rows if r["accepted_accuracy"]>=.90]
 return (max(eligible,key=lambda r:r["coverage"]) if eligible else max((r for r in rows if r["coverage"]>=.5),key=lambda r:r["accepted_accuracy"])),rows

def convert(model,path,quantized=False):
 import tensorflow as tf
 with tempfile.TemporaryDirectory() as d:
  model.export(d,format="tf_saved_model");cv=tf.lite.TFLiteConverter.from_saved_model(d);cv.target_spec.supported_ops=[tf.lite.OpsSet.TFLITE_BUILTINS]
  if quantized:cv.optimizations=[tf.lite.Optimize.DEFAULT]
  path.write_bytes(cv.convert())

def lite_scores(path,rows,c,n):
 import tensorflow as tf
 it=tf.lite.Interpreter(model_path=str(path),num_threads=4);it.allocate_tensors();ii=it.get_input_details()[0]["index"];oo=it.get_output_details()[0]["index"];scores=[];times=[]
 for r in rows:
  x=preprocess(location(c,"dataset_dir")/r["path"],c["image_size"]);start=time.perf_counter();it.set_tensor(ii,x);it.invoke();times.append((time.perf_counter()-start)*1000);scores.append(it.get_tensor(oo)[0])
 p=np.asarray(scores);assert p.shape==(len(rows),n);return p,times

def main(c):
 import tensorflow as tf
 m=load_manifest(c);root=location(c,"benchmark_dir");rank=read_json(root/"ranking_validation.json");classes=m["classes"]
 if len(rank)<14:raise ValueError(f"Benchmark incompleto: {len(rank)}/14")
 vr=[r for r in m["rows"] if r["split"]=="validation"];tr=[r for r in m["rows"] if r["split"]=="test"];yv=np.array([classes.index(r["class_id"]) for r in vr]);yt=np.array([classes.index(r["class_id"]) for r in tr])
 pool=[{"id":r["id"],"path":root/r["id"]/"model.keras","p":np.load(root/r["id"]/"validation_probabilities.npy"),"parameters":r["parameters"]} for r in rank]
 base=location(c,"output_dir")/"model.keras"
 if base.exists():
  model=tf.keras.models.load_model(base,compile=False);pool.append({"id":"baseline_anterior","path":base,"p":predict(model,c,vr),"parameters":model.count_params()});del model
 singles=[{"members":[x["id"]],"parameters":x["parameters"],**metrics(yv,x["p"])} for x in pool]
 top=sorted(pool,key=lambda x:metrics(yv,x["p"])["macro_f1"],reverse=True)[:6];ensembles=[]
 for n in (2,3):
  for combo in itertools.combinations(top,n):ensembles.append({"members":[x["id"] for x in combo],"parameters":sum(x["parameters"] for x in combo),**metrics(yv,np.mean([x["p"] for x in combo],axis=0))})
 candidates=sorted(singles+ensembles,key=lambda x:(x["macro_f1"],x["accuracy"],x["top3_accuracy"],-x["parameters"]),reverse=True);best_single=max(singles,key=lambda x:(x["macro_f1"],x["accuracy"]));best=candidates[0]
 if len(best["members"])>1 and best["macro_f1"]-best_single["macro_f1"]<.01:best=best_single
 pval=np.mean([next(x["p"] for x in pool if x["id"]==name) for name in best["members"]],axis=0);opt=minimize_scalar(lambda t:log_loss(yv,temp_scale(pval,t),labels=range(len(classes))),bounds=(.35,3),method="bounded");temperature=float(opt.x);threshold,curve=select_threshold(temp_scale(pval,temperature),yv)
 deploy=root/"deployment";deploy.mkdir(exist_ok=True);model_info=[];test_parts=[]
 for i,name in enumerate(best["members"],1):
  source=next(x["path"] for x in pool if x["id"]==name);model=tf.keras.models.load_model(source,compile=False);kp=predict(model,c,tr);test_parts.append(kp);keras=deploy/f"model_{i}_{name}.keras";shutil.copy2(source,keras)
  f32=deploy/f"model_{i}_{name}_float32.tflite";dyn=deploy/f"model_{i}_{name}_dynamic.tflite";convert(model,f32);convert(model,dyn,True);lp,times=lite_scores(f32,tr,c,len(classes));dp,_=lite_scores(dyn,tr,c,len(classes))
  model_info.append({"id":name,"keras":keras.name,"float32":f32.name,"dynamic":dyn.name,"float32_bytes":f32.stat().st_size,"dynamic_bytes":dyn.stat().st_size,"float32_sha256":sha256(f32),"dynamic_sha256":sha256(dyn),"float32_max_abs_error":float(np.max(np.abs(kp-lp))),"dynamic_max_abs_error":float(np.max(np.abs(kp-dp))),"float32_top1_agreement":float(np.mean(kp.argmax(1)==lp.argmax(1))),"dynamic_top1_agreement":float(np.mean(kp.argmax(1)==dp.argmax(1))),"desktop_median_ms":float(np.median(times))})
 ptest=temp_scale(np.mean(test_parts,axis=0),temperature);result=metrics(yt,ptest);pred=ptest.argmax(1);accepted=ptest.max(1)>=threshold["threshold"];result.update(n_images=len(yt),coverage=float(accepted.mean()),accepted_accuracy=float((pred[accepted]==yt[accepted]).mean()) if accepted.any() else None,classification_report=classification_report(yt,pred,labels=range(len(classes)),target_names=classes,output_dict=True,zero_division=0),confusion_matrix=confusion_matrix(yt,pred,labels=range(len(classes))).tolist())
 manifest={"schema_version":2,"input_shape":[1,c["image_size"],c["image_size"],3],"input_dtype":"float32","pixel_range":[0,255],"classes":classes,"classes_sha256":classes_hash(classes),"ensemble":"mean_probabilities","members":model_info,"temperature":temperature,"confidence_threshold":threshold["threshold"],"selection_validation":best,"test_used_for_selection":False,"test_metrics":result}
 write_json(deploy/"ranking_validation.json",candidates);write_json(deploy/"threshold_validation.json",{"temperature":temperature,"selected":threshold,"curve":curve});write_json(deploy/"deployment_manifest.json",manifest);write_json(deploy/"test_predictions.json",[{"path":r["path"],"true_class":r["class_id"],"probabilities":p.tolist()} for r,p in zip(tr,ptest)]);print(json.dumps({"selected":best,"temperature":temperature,"threshold":threshold,"test":{k:v for k,v in result.items() if k not in {"classification_report","confusion_matrix"}},"models":model_info},indent=2))

if __name__=="__main__":
 p=argparse.ArgumentParser();p.add_argument("--config",default="config.benchmark.yaml");a=p.parse_args();main(load_config(a.config))
