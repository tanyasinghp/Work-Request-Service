# Cap Low-priority requests per batch

As an operations lead, I want a per-batch cap on Low-priority requests, so that one sender
flooding a batch with Low work can't crowd out everything else.

The cap is configurable. Requests over the cap are reported as problems, never silently
dropped, and which Low requests are kept must not depend on the order of the input.
