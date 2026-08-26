# FinBERT evaluation gate

The production sentiment adapter uses `ProsusAI/finbert` revision
`4556d13015211d73dccd3fdd39d39232506f3e43`. The keyword analyzer is only a
deliberate baseline selected with `SENTIMENT_PROVIDER=keyword`.

Before changing the production model or preprocessing version, evaluate a
held-out, manually labelled set of crypto-finance headlines with all three
labels represented. Record the model revision, preprocessing version, sample
count, class counts, confusion matrix, and macro-F1. Promotion requires:

- macro-F1 at least `0.70`;
- no class F1 below `0.55`;
- no overlap between tuning and held-out samples;
- the signed stored score remains `P(positive) - P(negative)`.

`SentimentEvaluation.macroF1` is the shared Java calculation used by evaluation
jobs and tests. Hosted inference is intentionally not called during the normal
offline test suite; CI or a release operator runs the labelled evaluation with
an `HF_TOKEN` before changing the pinned revision.
