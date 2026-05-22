# StreamVault ideas to revisit

Source reviewed: https://github.com/Davidona/StreamVault-IPTV

License note: StreamVault is source-available, non-commercial and share-alike. Treat this as architectural inspiration unless the license obligations are acceptable.

## Started

- Move image loading toward Coil with a single app-level image loader.
- Introduce a shared OkHttp client for API and image networking.
- Add Room performance indices for catalog browsing, history, favorites, and resume metadata.
- Batch catalog cache writes in a Room transaction.
- Move catalog filtering and sorting away from the Compose UI thread.
- Persist precomputed catalog search/filter metadata during sync and reuse it when loading from Room cache.

## Next candidates

- Add richer image fallbacks: poster initials, gradient placeholders, and stateful error fallback refinements.
- Consider Android TV integrations later: launcher recommendations and Watch Next.
