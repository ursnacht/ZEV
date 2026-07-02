-- Add a JSONB column holding tenant-specific feature-flag overrides on the organisation table.
-- Structure: flat object { "<FLAG_KEY>": true|false, ... }. Only explicitly set flags are stored;
-- absent keys fall back to the global default declared in the FeatureFlag enum.
-- Additive and nullable: existing tenants (NULL) behave as "no overrides" (all defaults).
ALTER TABLE zev.organisation
    ADD COLUMN feature_flags JSONB;

COMMENT ON COLUMN zev.organisation.feature_flags IS 'Mandantenspezifische Feature-Flag-Ueberschreibungen als flaches JSON-Objekt { "FLAG_KEY": boolean }. NULL = keine Ueberschreibungen (globale Defaults gelten).';
