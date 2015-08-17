-- store image size on upload
-- to not touch files when someone requests stats
ALTER TABLE zp_data.images
 ADD COLUMN i_size INTEGER;
