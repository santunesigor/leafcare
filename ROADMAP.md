# LeafCare roadmap

This roadmap separates what is already complete from what is still required for an academic prototype, a reliable field pilot and a production-ready application.

## Current baseline

- [x] Native Android application in Kotlin and Jetpack Compose.
- [x] CameraX capture and gallery import.
- [x] Offline TFLite inference.
- [x] Top-3 predictions and inconclusive state.
- [x] Local Room history, search, filter and deletion.
- [x] Photo-taking guidance using the supplied examples.
- [x] Reproducible dataset audit and group-aware split.
- [x] MobileNetV3Small transfer learning in two stages.
- [x] Baseline evaluation and Keras/TFLite parity check.
- [x] Benchmark of 14 additional configurations.
- [x] Experimental calibrated ensemble exported to TFLite.
- [x] Architecture, dataset, validation and license documentation.

## Phase 1 — close the academic prototype

Priority: **required before presenting the project as complete**.

- [ ] Run the app on at least one physical Android phone.
- [ ] Execute every item in `docs/MANUAL_TESTS.md`.
- [ ] Run `connectedDebugAndroidTest` on a phone or emulator.
- [ ] Test camera permission denial, retry and permanent denial.
- [ ] Test gallery selection, rotation, large images and corrupted files.
- [ ] Confirm Room persistence after closing and reopening the process.
- [ ] Measure end-to-end inference time on the selected presentation phone.
- [ ] Review every disease name, description, symptom and guidance with an agricultural professional.
- [ ] Confirm with the professor whether the current 80.58% ensemble result and the documented limitations meet the course objective.
- [ ] Record a short demonstration video showing the complete offline flow.
- [ ] Assign and document each team member's final responsibilities.

**Exit criterion:** the app completes the main flow on a real phone without internet, all manual critical tests pass and the team can explain the dataset, metrics and limitations.

## Phase 2 — decide the production inference strategy

The app currently uses the single MobileNetV3Small baseline. The benchmark recommends a three-model ensemble, but it must not replace the baseline without mobile validation.

- [ ] Implement the ensemble in an isolated feature branch.
- [ ] Load and reuse three interpreters instead of recreating them per image.
- [ ] Average the 16 output probabilities in the documented class order.
- [ ] Apply temperature `0.8421423931819505`.
- [ ] Apply the calibrated inconclusive threshold `0.74`.
- [ ] Create a Kotlin/Python parity fixture for all 16 probabilities.
- [ ] Compare APK size, startup time, memory, heat and battery usage.
- [ ] Compare latency on an entry-level and a mid-range Android phone.
- [ ] Keep the baseline as fallback on devices where the ensemble is too costly.
- [ ] Consider knowledge distillation if the ensemble gain cannot justify three inferences.

**Exit criterion:** choose between baseline, ensemble or distilled model using measured Android results rather than desktop timings.

## Phase 3 — improve model reliability

Priority: **largest expected impact on real accuracy**.

- [ ] Collect more independent field images, especially from Southern Brazil.
- [ ] Increase the rare classes: anthracnose, TSWV, black shank and genetic abnormality.
- [ ] Store property, field, plant, date and capture-session identifiers.
- [ ] Split future datasets by property or plant, not only by filename/hash.
- [ ] Obtain double label review from an agricultural specialist.
- [ ] Create an external test set that remains locked until the model is frozen.
- [ ] Repeat shortlisted models with multiple seeds.
- [ ] Use grouped cross-validation and report mean plus standard deviation.
- [ ] Add calibration metrics: ECE, multiclass Brier score and reliability diagram.
- [ ] Recalibrate temperature and threshold using a dedicated calibration subset.
- [ ] Evaluate per-class precision, recall and F1 with confidence intervals.

**Exit criterion:** demonstrate that the selected model generalizes to independent properties and that confidence values are meaningfully calibrated.

## Phase 4 — reject unsuitable photographs

The current closed-set classifier always distributes probability among the 16 known classes.

- [ ] Add blur detection.
- [ ] Add low-light and overexposure detection.
- [ ] Detect when the leaf is too distant or too small in the frame.
- [ ] Add negative samples: hands, soil, tools, other crops and backgrounds.
- [ ] Add an out-of-distribution or “not a tobacco leaf” decision.
- [ ] Ask for a new photograph when quality checks fail.
- [ ] Evaluate whether lesion detection/cropping improves small-symptom classes.

**Exit criterion:** clearly unsuitable or unrelated photographs must not produce a confident disease result.

## Phase 5 — complete field workflow

- [ ] Add optional property and field identifiers to an analysis.
- [ ] Add notes and symptom observations.
- [ ] Allow editing metadata without changing the original prediction.
- [ ] Export a local report for an agricultural technician.
- [ ] Define retention and deletion behavior for stored photographs.
- [ ] Add backup/export without making cloud access mandatory.
- [ ] Validate text size, contrast and touch targets outdoors.
- [ ] Test the interface with actual producers.

## Phase 6 — release engineering

- [ ] Add CI for Python tests and Android unit tests.
- [ ] Add reproducible release versioning and changelog.
- [ ] Configure a private Android signing key outside Git.
- [ ] Produce a signed release APK/AAB.
- [ ] Run dependency and license checks.
- [ ] Confirm dataset, reference-image, font and pretrained-weight redistribution rights.
- [ ] Remove debug-only content and verify that no secrets or absolute paths are committed.
- [ ] Create GitHub releases instead of committing APK files to the repository.
- [ ] Add installation instructions and release checksums.

## Recommended order

1. Physical-device validation and professor review.
2. Agronomic review of labels and content.
3. Ensemble Android experiment and performance decision.
4. New field data and external validation.
5. Photo-quality and out-of-distribution rejection.
6. Property/field workflow and user testing.
7. Signed release and distribution.

## Definition of done

LeafCare should only be described as ready for field use when:

- its primary flow works offline on supported real devices;
- the model is validated on independent field data;
- rare classes have sufficient evaluation samples;
- confidence and inconclusive behavior are calibrated;
- unsuitable images are rejected;
- disease content is reviewed by a qualified professional;
- privacy, licensing and Android release requirements are satisfied.
