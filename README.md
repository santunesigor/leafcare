# LeafCare

> Android app in development for offline visual screening of diseases and abnormalities in tobacco leaves using computer vision.

## overview

LeafCare is an academic project focused on bringing image-based screening directly to the field, without depending on an internet connection.

The idea is simple: capture or select a leaf image, run the model locally on the device and return the most likely classes with confidence scores. When confidence is too low, the app should prefer an inconclusive result instead of forcing a prediction.

## planned experience

- capture or select a leaf image
- offline inference on Android
- top-3 predicted classes with confidence scores
- inconclusive result when confidence is insufficient
- local history of previous analyses
- guidance for taking better photos
- disease/condition information inside the app

## stack

`Kotlin` · `Android` · `Python` · `TensorFlow / LiteRT` · `Machine Learning`

The current ML direction uses **MobileNetV3Small with transfer learning**, with the final model intended to be exported for mobile inference.
