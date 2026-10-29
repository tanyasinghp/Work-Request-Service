# Flag stale requests

As a maintenance supervisor, I want requests older than 30 days to be flagged as stale in
the problems list, so that old reports that may no longer be relevant get reviewed instead
of silently joining the queue.

"Older than 30 days" is measured against the time the batch is processed. A stale request
is reported as a problem; whether it is also kept out of the queue is a decision the
acceptance criteria must state explicitly.
