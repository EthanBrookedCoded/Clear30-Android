CREATE POLICY "Give users access to own folder 1ajt695_1" ON storage.objects FOR INSERT TO public WITH CHECK (bucket_id = 'community' AND (select get_user_id()) = (storage.foldername(name))[1]);

CREATE POLICY "Give users access to own folder 1ajt695_0" ON storage.objects FOR SELECT TO public USING (bucket_id = 'community' AND (select get_user_id()) = (storage.foldername(name))[1]);

CREATE POLICY "Give users access to own folder 1ajt695_2" ON storage.objects FOR UPDATE TO public USING (bucket_id = 'community' AND (select get_user_id()) = (storage.foldername(name))[1]);

CREATE POLICY "Give users access to own folder 1ajt695_3" ON storage.objects FOR DELETE TO public USING (bucket_id = 'community' AND (select get_user_id()) = (storage.foldername(name))[1]);
