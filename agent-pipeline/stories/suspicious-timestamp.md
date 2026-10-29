# Report future timestamps

As an integrator sending batches from several sites, I want a SUSPICIOUS_TIMESTAMP problem
for any request whose requestedAt is in the future, so that clock or time-zone bugs in a
sending system are caught instead of distorting the queue order.

"In the future" is relative to the time the batch is processed, and the check must be
testable without depending on the real clock.
