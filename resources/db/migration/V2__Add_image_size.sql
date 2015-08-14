-- store image size on upload
-- to not touch files when someone requests stats
ALTER TABLE zp_data.images
 ADD COLUMN i_size INTEGER DEFAULT 0;

UPDATE zp_data.images
   SET i_size = 0;