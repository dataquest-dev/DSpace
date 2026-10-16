# External handle catalogue privacy

`GET /api/services/handles/magic` remains a public catalogue of shortener
destinations. Its `HandleRest` summary includes the handle, destination, title,
repository, submission date and subprefix. The internal submitter contact
`reportemail` is write-only and is omitted from serialized catalogue responses.
The service may still use that contact internally. This does not remove
intentionally public item contact metadata or change the separate owner-capability
create/update request model.

The core administrative handle registry contains the encoded shortener record.
This catalogue change must be combined with the R01 restriction on core handle
registry reads to protect that separate path. Neither change makes an existing
disclosed contact private retroactively.
