-- Seed: Free / Promo Codes
-- FRE2ME is the universal free-access code for local dev and gifting

INSERT INTO payment.promo_codes (code, uses)
VALUES ('free2me', 0)
ON CONFLICT (code) DO NOTHING;
