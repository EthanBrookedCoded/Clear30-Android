
create sequence "payment"."sale_users_id_seq";

create table "payment"."sale_users" (
    "id" bigint not null default nextval('payment.sale_users_id_seq'::regclass),
    "user_id" text not null,
    "sale_id" bigint not null,
    "created_at" timestamp with time zone default CURRENT_TIMESTAMP
);


alter table "payment"."sale_users" enable row level security;

alter table "payment"."sale" alter column "ends_on" set data type timestamp with time zone using "ends_on"::timestamp with time zone;

alter table "payment"."sale" alter column "starts_on" set data type timestamp with time zone using "starts_on"::timestamp with time zone;

alter sequence "payment"."sale_users_id_seq" owned by "payment"."sale_users"."id";

CREATE INDEX idx_sale_users_sale_id ON payment.sale_users USING btree (sale_id);

CREATE INDEX idx_sale_users_user_id ON payment.sale_users USING btree (user_id);

CREATE UNIQUE INDEX sale_users_pkey ON payment.sale_users USING btree (id);

CREATE UNIQUE INDEX sale_users_user_id_sale_id_key ON payment.sale_users USING btree (user_id, sale_id);

alter table "payment"."sale_users" add constraint "sale_users_pkey" PRIMARY KEY using index "sale_users_pkey";

alter table "payment"."sale_users" add constraint "sale_users_sale_id_fkey" FOREIGN KEY (sale_id) REFERENCES payment.sale(id) ON DELETE CASCADE not valid;

alter table "payment"."sale_users" validate constraint "sale_users_sale_id_fkey";

alter table "payment"."sale_users" add constraint "sale_users_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE not valid;

alter table "payment"."sale_users" validate constraint "sale_users_user_id_fkey";

alter table "payment"."sale_users" add constraint "sale_users_user_id_sale_id_key" UNIQUE using index "sale_users_user_id_sale_id_key";

create policy "Disable public access"
on "payment"."sale_users"
as permissive
for all
to public
using (false);



set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.payment_check_sale()
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
  active_sale_id bigint;
  sale_exists boolean;
  v_user_id text;
  has_participated boolean;
BEGIN
  -- Get the user's ID using the public.get_user_id function
  v_user_id := public.get_user_id();
  
  -- Throw an error if no user found
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'User not found';
  END IF;
  
  -- Check if there's an active sale (current date between starts_on and ends_on)
  SELECT id INTO active_sale_id
  FROM payment.sale
  WHERE CURRENT_DATE BETWEEN starts_on AND ends_on
  LIMIT 1;
  
  -- Determine if a sale exists
  sale_exists := active_sale_id IS NOT NULL;
  
  -- If a sale exists
  IF sale_exists THEN
    -- Increment the uses count
    UPDATE payment.sale
    SET uses = uses + 1
    WHERE id = active_sale_id;
    
    -- Add a record to sale_users table (if it doesn't exist already)
    INSERT INTO payment.sale_users (user_id, sale_id)
    VALUES (v_user_id, active_sale_id)
    ON CONFLICT (user_id, sale_id) DO NOTHING;
    
    RETURN TRUE;
  ELSE
    -- No active sale, check if user has participated in any sale before
    SELECT EXISTS (
      SELECT 1 
      FROM payment.sale_users 
      WHERE user_id = v_user_id
    ) INTO has_participated;
    
    -- Return true if user has participated in a sale before
    RETURN has_participated;
  END IF;
END;$function$
;




create sequence "events"."event_users_id_seq";

create table "events"."event_users" (
    "id" integer not null default nextval('events.event_users_id_seq'::regclass),
    "user_id" text not null,
    "event_id" text not null,
    "entered_at" timestamp with time zone default now()
);


alter table "events"."event_users" enable row level security;

alter sequence "events"."event_users_id_seq" owned by "events"."event_users"."id";

CREATE UNIQUE INDEX event_users_pkey ON events.event_users USING btree (id);

CREATE UNIQUE INDEX event_users_user_id_event_id_key ON events.event_users USING btree (user_id, event_id);

alter table "events"."event_users" add constraint "event_users_pkey" PRIMARY KEY using index "event_users_pkey";

alter table "events"."event_users" add constraint "event_users_event_id_fkey" FOREIGN KEY (event_id) REFERENCES events.events(id) not valid;

alter table "events"."event_users" validate constraint "event_users_event_id_fkey";

alter table "events"."event_users" add constraint "event_users_user_id_event_id_key" UNIQUE using index "event_users_user_id_event_id_key";

alter table "events"."event_users" add constraint "event_users_user_id_fkey" FOREIGN KEY (user_id) REFERENCES users(id) not valid;

alter table "events"."event_users" validate constraint "event_users_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION events.check_event_sale()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    current_sale_id BIGINT;
BEGIN
    -- Only run this function on INSERT operations
    IF TG_OP != 'INSERT' THEN
        RETURN NEW;
    END IF;
    
    -- Check if there is a running sale in the payment.sale table
    -- A running sale is one where current timestamp is between starts_on and ends_on
    SELECT id INTO current_sale_id
    FROM payment.sale
    WHERE NOW() BETWEEN starts_on AND ends_on
    LIMIT 1;
    
    -- If a current sale exists, add the user to the sale_users table
    IF current_sale_id IS NOT NULL THEN
        -- Add an entry to payment.sale_users
        INSERT INTO payment.sale_users (user_id, sale_id)
        VALUES (NEW.user_id, current_sale_id)
        ON CONFLICT (user_id, sale_id) DO NOTHING;
        
        -- Increment the uses counter in the sale table
        UPDATE payment.sale
        SET uses = uses + 1
        WHERE id = current_sale_id;
    END IF;
    
    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    -- Log any errors that occur but don't fail the transaction
    RAISE NOTICE 'Error in events.check_event_sale function: %', SQLERRM;
    RETURN NEW;
END;
$function$
;
CREATE OR REPLACE FUNCTION events.handle_event_users()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$DECLARE
    v_logging_id TEXT;
    current_event_id TEXT;
    matching_user_id TEXT;
BEGIN
    -- Extract the logging_id from the user_id field
    v_logging_id := NEW.user_id;
    
    IF v_logging_id IS NULL THEN
        RETURN NEW;
    END IF;
    
    -- Check if the event is 'event_entered'
    IF NEW.event = 'event_entered' THEN
        -- Get the current running event based on onboarding dates
        -- Include a 1-day grace period on both start and end dates
        SELECT id INTO current_event_id 
        FROM events.events 
        WHERE CURRENT_DATE BETWEEN (onboarding_start_date - INTERVAL '1 day') AND (onboarding_end_date + INTERVAL '1 day')
        LIMIT 1;
        
        IF current_event_id IS NULL THEN
            RETURN NEW; -- No active event found
        END IF;
        
        -- First try: Get the user_id for the user matching that logging_id in the array
        SELECT id INTO matching_user_id 
        FROM public.users 
        WHERE logging_id @> ARRAY[v_logging_id]
        LIMIT 1;
        
        -- Second try: If no user found with logging_id array match, try finding a user with id = logging_id
        IF matching_user_id IS NULL THEN
            SELECT id INTO matching_user_id 
            FROM public.users 
            WHERE id = v_logging_id
            LIMIT 1;
            
            IF matching_user_id IS NULL THEN
                RETURN NEW; -- No matching user found with either method
            END IF;
        END IF;
        
        -- Add a row to the events.event_users table
        INSERT INTO events.event_users (user_id, event_id, entered_at)
        VALUES (matching_user_id, current_event_id, NOW())
        ON CONFLICT (user_id, event_id) DO NOTHING;
        
    -- Check if the event is 'event_left'
    ELSIF NEW.event = 'event_left' THEN
        -- First try: Get the user_id for the user matching that logging_id in the array
        SELECT id INTO matching_user_id 
        FROM public.users 
        WHERE logging_id @> ARRAY[v_logging_id]
        LIMIT 1;
        
        -- Second try: If no user found with logging_id array match, try finding a user with id = logging_id
        IF matching_user_id IS NULL THEN
            SELECT id INTO matching_user_id 
            FROM public.users 
            WHERE id = v_logging_id
            LIMIT 1;
            
            IF matching_user_id IS NULL THEN
                RETURN NEW; -- No matching user found with either method
            END IF;
        END IF;
        
        -- Remove the row from the events.event_users table
        DELETE FROM events.event_users
        WHERE user_id = matching_user_id;
    END IF;
    
    RETURN NEW;
END;$function$
;


create policy "Disable all access"
on "events"."event_users"
as permissive
for all
to public
using (false);


CREATE TRIGGER trigger_check_event_sale AFTER INSERT ON events.event_users FOR EACH ROW EXECUTE FUNCTION events.check_event_sale();


CREATE TRIGGER trigger_handle_event_users AFTER INSERT ON public.events FOR EACH ROW EXECUTE FUNCTION events.handle_event_users();