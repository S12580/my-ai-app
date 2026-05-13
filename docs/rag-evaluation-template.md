# RAG Evaluation Template

## Goal

Track retrieval and answer quality for document QA MVP before optimization.

## Dataset

- Size: 20-50 QA pairs
- Source: uploaded docs used in staging
- Distribution:
  - fact lookup
  - long context summary
  - ambiguous/no-answer

## Metrics

- Retrieval hit@k (`k=5`)
- Grounded answer rate
- No-answer correctness
- End-to-end latency (p50/p95)

## Log Schema

| case_id | question | expected_doc | hit_at_5 | answer_ok | grounded | latency_ms | notes |
|---|---|---|---|---|---|---|---|

## Pass Criteria (MVP)

- hit@5 >= 0.75
- grounded rate >= 0.80
- p95 latency <= 5000 ms
- no-answer correctness >= 0.80

