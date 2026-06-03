-- Add price_string column to product_discount_codes table
ALTER TABLE payment.product_discount_codes 
ADD COLUMN price_string text;

