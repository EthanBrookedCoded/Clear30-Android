-- Drop old index
DROP INDEX IF EXISTS payment.product_discount_codes_product_id;

-- Rename column from product_id to entitlement
ALTER TABLE payment.product_discount_codes 
RENAME COLUMN product_id TO entitlement;

-- Create new index on entitlement
CREATE INDEX product_discount_codes_entitlement ON payment.product_discount_codes USING btree (entitlement);

