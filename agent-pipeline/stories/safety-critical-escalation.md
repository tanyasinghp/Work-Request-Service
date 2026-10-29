# Auto-escalate safety-critical equipment

As a maintenance planner, I want requests for equipment on a safety-critical list to be
auto-escalated to Critical, so that a low-priority report about, say, a fire pump or an
elevator brake is never left waiting behind routine work.

The list of safety-critical equipment ids is configuration, not code. Escalated requests
should still be explainable: the response must make it clear that a request was raised
to Critical because its equipment is safety-critical.
