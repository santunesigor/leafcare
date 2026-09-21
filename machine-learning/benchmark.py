"""Benchmark reproduzível de modelos móveis; seleção usa somente validação."""
import argparse,gc,json,time
from pathlib import Path
import numpy as np
from leafcare.common import load_config,location,write_json
from leafcare.dataset import load_manifest
from leafcare.preprocessing import preprocess

CANDIDATES=[
 {"id":"m3s_base","arch":"MobileNetV3Small","drop":.25,"aug":"standard","ft":30,"lr":1e-3},
 {"id":"m3s_strong","arch":"MobileNetV3Small","drop":.35,"aug":"strong","ft":45,"lr":7e-4},
 {"id":"m3l_base","arch":"MobileNetV3Large","drop":.30,"aug":"standard","ft":40,"lr":7e-4},
 {"id":"m3l_strong","arch":"MobileNetV3Large","drop":.40,"aug":"strong","ft":60,"lr":5e-4},
 {"id":"effb0_base","arch":"EfficientNetB0","drop":.30,"aug":"standard","ft":40,"lr":7e-4},
 {"id":"effb0_strong","arch":"EfficientNetB0","drop":.40,"aug":"strong","ft":60,"lr":5e-4},
 {"id":"effv2b0_base","arch":"EfficientNetV2B0","drop":.30,"aug":"standard","ft":40,"lr":7e-4},
 {"id":"effv2b0_strong","arch":"EfficientNetV2B0","drop":.40,"aug":"strong","ft":60,"lr":5e-4},
 {"id":"m2_base","arch":"MobileNetV2","drop":.30,"aug":"standard","ft":40,"lr":7e-4},
 {"id":"nasnet_base","arch":"NASNetMobile","drop":.35,"aug":"standard","ft":50,"lr":5e-4},
 {"id":"m3s_adam","arch":"MobileNetV3Small","drop":.25,"aug":"standard","ft":30,"lr":1e-3,"optimizer":"adam"},
 {"id":"m3s_no_weights","arch":"MobileNetV3Small","drop":.25,"aug":"standard","ft":30,"lr":1e-3,"balance":"none"},
 {"id":"m3s_rmsprop","arch":"MobileNetV3Small","drop":.25,"aug":"standard","ft":30,"lr":7e-4,"optimizer":"rmsprop"},
 {"id":"m3s_low_dropout","arch":"MobileNetV3Small","drop":.15,"aug":"standard","ft":45,"lr":7e-4},
]

def make_ds(c,m,split,aug="none"):
 import tensorflow as tf
 rows=[r for r in m["rows"] if r["split"]==split];ids={x:i for i,x in enumerate(m["classes"])};size=c["image_size"]
 def gen():
  for r in rows:yield preprocess(location(c,"dataset_dir")/r["path"],size)[0],np.int32(ids[r["class_id"]])
 ds=tf.data.Dataset.from_generator(gen,output_signature=(tf.TensorSpec((size,size,3),tf.float32),tf.TensorSpec((),tf.int32))).apply(tf.data.experimental.assert_cardinality(len(rows)))
 if aug!="none":ds=ds.shuffle(len(rows),seed=c["seed"],reshuffle_each_iteration=True)
 ds=ds.batch(c["batch_size"])
 if aug!="none":
  layers=[tf.keras.layers.RandomFlip("horizontal_and_vertical",seed=43),tf.keras.layers.RandomRotation(.08,fill_mode="reflect",seed=44),tf.keras.layers.RandomZoom(.1,fill_mode="reflect",seed=45),tf.keras.layers.RandomContrast(.12,seed=46)]
  if aug=="strong":layers += [tf.keras.layers.RandomTranslation(.08,.08,fill_mode="reflect",seed=47),tf.keras.layers.RandomBrightness(.12,value_range=(0.,255.),seed=48)]
  tr=tf.keras.Sequential(layers);ds=ds.map(lambda x,y:(tf.clip_by_value(tr(x,training=True),0.,255.),y),num_parallel_calls=2)
 return ds.prefetch(1)

def build(s,n,size):
 import tensorflow as tf
 app=getattr(tf.keras.applications,s["arch"]);kw=dict(input_shape=(size,size,3),include_top=False,weights="imagenet",pooling="avg")
 if s["arch"] in {"MobileNetV3Small","MobileNetV3Large","EfficientNetV2B0"}:kw["include_preprocessing"]=True
 base=app(**kw);base.trainable=False;inp=tf.keras.Input((size,size,3),name="rgb_0_255");x=inp
 if s["arch"] in {"MobileNetV2","NASNetMobile"}:x=tf.keras.layers.Rescaling(1/127.5,offset=-1,name="embedded_rescaling")(x)
 x=base(x,training=False);x=tf.keras.layers.Dropout(s["drop"])(x);out=tf.keras.layers.Dense(n,activation="softmax",name="probabilities")(x)
 return tf.keras.Model(inp,out,name="leafcare_"+s["id"]),base

def compile_model(model,lr,s):
 import tensorflow as tf
 if s.get("optimizer")=="adam":opt=tf.keras.optimizers.Adam(lr)
 elif s.get("optimizer")=="rmsprop":opt=tf.keras.optimizers.RMSprop(lr,momentum=.9)
 else:opt=tf.keras.optimizers.AdamW(lr,weight_decay=1e-5)
 model.compile(opt,"sparse_categorical_crossentropy",metrics=["accuracy"])

def score(y,p):
 from sklearn.metrics import f1_score
 pred=p.argmax(1)
 return {"accuracy":float(np.mean(pred==y)),"macro_f1":float(f1_score(y,pred,average="macro",zero_division=0)),"top3_accuracy":float(np.mean([v in np.argsort(-q)[:3] for v,q in zip(y,p)])),"mean_confidence":float(p.max(1).mean())}

def run(c,only=None):
 import tensorflow as tf
 tf.config.threading.set_inter_op_parallelism_threads(2);tf.config.threading.set_intra_op_parallelism_threads(6);tf.config.experimental.enable_op_determinism()
 m=load_manifest(c);root=location(c,"benchmark_dir");root.mkdir(parents=True,exist_ok=True)
 val=make_ds(c,m,"validation");rows=[r for r in m["rows"] if r["split"]=="validation"];y=np.array([m["classes"].index(r["class_id"]) for r in rows])
 from collections import Counter
 counts=Counter(r["class_id"] for r in m["rows"] if r["split"]=="train");total=sum(counts.values());cw={i:total/(len(counts)*counts[x]) for i,x in enumerate(m["classes"])}
 selected=[s for s in CANDIDATES if only in (None,s["id"])]
 if not selected:raise ValueError("Candidato desconhecido")
 for index,s in enumerate(selected):
  out=root/s["id"]
  if (out/"validation.json").exists():print(s["id"],"já concluído");continue
  out.mkdir(parents=True,exist_ok=True);tf.keras.backend.clear_session();tf.keras.utils.set_random_seed(c["seed"]+CANDIDATES.index(s))
  model,base=build(s,len(m["classes"]),c["image_size"]);train=make_ds(c,m,"train",s["aug"]);used=cw if s.get("balance","weights")=="weights" else None
  def callbacks(path):return [tf.keras.callbacks.ModelCheckpoint(path,monitor="val_loss",save_best_only=True,save_weights_only=True),tf.keras.callbacks.EarlyStopping(monitor="val_loss",patience=3,restore_best_weights=True),tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss",patience=2,factor=.35,min_lr=1e-7),tf.keras.callbacks.TerminateOnNaN()]
  started=time.time();compile_model(model,s["lr"],s);frozen=out/"frozen.weights.h5";h1=model.fit(train,validation_data=val,epochs=c["benchmark"]["frozen_epochs"],class_weight=used,callbacks=callbacks(frozen),verbose=2);model.load_weights(frozen);loss1=float(model.evaluate(val,verbose=0)[0])
  base.trainable=True;start=len(base.layers)-s["ft"]
  for i,l in enumerate(base.layers):l.trainable=i>=start and not isinstance(l,tf.keras.layers.BatchNormalization)
  compile_model(model,c["benchmark"]["finetune_lr"],s);tuned=out/"tuned.weights.h5";h2=model.fit(train,validation_data=val,epochs=c["benchmark"]["finetune_epochs"],class_weight=used,callbacks=callbacks(tuned),verbose=2);model.load_weights(tuned);loss2=float(model.evaluate(val,verbose=0)[0]);stage="finetune"
  if loss1<=loss2:model.load_weights(frozen);stage="frozen"
  model.save(out/"model.keras");p=model.predict(val,verbose=0);np.save(out/"validation_probabilities.npy",p)
  result={**s,**score(y,p),"selected_stage":stage,"frozen_val_loss":loss1,"finetune_val_loss":loss2,"epochs_frozen":len(h1.history["loss"]),"epochs_finetune":len(h2.history["loss"]),"seconds":time.time()-started,"parameters":model.count_params(),"keras_bytes":(out/"model.keras").stat().st_size}
  write_json(out/"history.json",{"frozen":h1.history,"finetune":h2.history});write_json(out/"validation.json",result);print(json.dumps(result));gc.collect()
 results=[json.loads(p.read_text()) for p in root.glob("*/validation.json")];results.sort(key=lambda x:(x["macro_f1"],x["accuracy"],x["top3_accuracy"]),reverse=True);write_json(root/"ranking_validation.json",results)

if __name__=="__main__":
 p=argparse.ArgumentParser();p.add_argument("--config",default="config.benchmark.yaml");p.add_argument("--only");a=p.parse_args();run(load_config(a.config),a.only)
