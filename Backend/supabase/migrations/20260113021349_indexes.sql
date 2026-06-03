CREATE INDEX idx_dr_fred_user_created ON comms.dr_fred USING btree (user_id, created_at DESC);

CREATE INDEX idx_dr_fred_user_id ON comms.dr_fred USING btree (user_id);

CREATE INDEX idx_feedback_timestamp ON comms.feedback USING btree ("timestamp" DESC);

CREATE INDEX idx_sms_messages_conversation_lookup ON comms.sms_messages USING btree (user_id, created_at DESC) INCLUDE (phone_number, text, outbound, scheduled_for, canceled);

CREATE INDEX idx_sms_messages_created_at ON comms.sms_messages USING btree (created_at DESC);

CREATE INDEX idx_sms_messages_nonuser_conversations ON comms.sms_messages USING btree (phone_number, created_at DESC, scheduled_for) WHERE ((canceled = false) AND (user_id IS NULL));

CREATE INDEX idx_sms_messages_phone_number ON comms.sms_messages USING btree (phone_number);

CREATE INDEX idx_sms_messages_scheduled_for ON comms.sms_messages USING btree (scheduled_for DESC);

CREATE INDEX idx_sms_messages_user_conversations ON comms.sms_messages USING btree (user_id, created_at DESC, scheduled_for) WHERE ((canceled = false) AND (user_id IS NOT NULL));

CREATE INDEX idx_sms_messages_user_id ON comms.sms_messages USING btree (user_id) WHERE (user_id IS NOT NULL);


CREATE INDEX idx_program_assessment_responses_user_timestamp ON programs.program_assessment_responses USING btree (user_id, "timestamp" DESC);


