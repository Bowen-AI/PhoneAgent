# JSONL benchmark schema v1

One JSON object per completed/failed/timeout task. Required: `run_id`, `task`, `device`, `model`, `backend`, `mode`, `condition`, `success` (boolean), `total_ms` (finite nonnegative).

Illustrative example below—**NOT a measurement**:

```json
{"run_id":"EXAMPLE-1","task":"media_discovery","device":"Example Pixel","model":"placeholder","backend":"CPU","mode":"local","condition":"warm","success":false,"timeout":true,"total_ms":20000,"steps":3,"error_type":"timeout","phases_ms":{"intent_ms":14,"observe_ms":90,"llm_ms":17000,"action_ms":200,"verify_ms":50}}
```

The sum of phase spans need not equal end-to-end time (overlap/waits). A tap dispatch is not task success. Protected content and restricted permissions may make a task untestable; record an explicit blocker rather than marking it a pass.
