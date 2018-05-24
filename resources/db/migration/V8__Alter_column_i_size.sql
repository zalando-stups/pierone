-- enlarge possible uploaded image size on upload
ALTER TABLE zp_data.images
    ALTER COLUMN i_size TYPE BIGINT USING i_size::bigint;