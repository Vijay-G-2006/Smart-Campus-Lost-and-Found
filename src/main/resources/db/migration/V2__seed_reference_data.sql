-- ---------------------------------------------------------------------------
-- Reference data: item categories and campus locations.
-- Synthetic, campus neutral data - no personal information.
-- ---------------------------------------------------------------------------

INSERT INTO categories (name, description) VALUES
    ('Electronics', 'Phones, laptops, chargers, headphones and other electronic devices'),
    ('Keys', 'Keys, key rings, access cards and safes'),
    ('Wallets & Bags', 'Wallets, backpacks, handbags, umbrellas and luggage'),
    ('Clothing', 'Jackets, hoodies, hats, gloves and other clothing'),
    ('Books & Stationery', 'Books, notebooks, pens and study material'),
    ('ID Cards & Documents', 'Student cards, certificates, passports and folders'),
    ('Sports Equipment', 'Sports gear, balls, rackets and gym equipment'),
    ('Glasses', 'Prescription glasses and sunglasses'),
    ('Music & Gaming', 'Instruments, headphones and gaming accessories'),
    ('Other', 'Everything that does not fit into another category');

INSERT INTO locations (name, description) VALUES
    ('Main Library', 'Central library building with study rooms and desks'),
    ('Student Centre', 'Student union, cafe and common area'),
    ('Science Block A', 'Lecture halls and laboratories of the science faculty'),
    ('Science Block B', 'Lecture halls and laboratories of the science faculty'),
    ('Engineering Block', 'Engineering workshops, labs and project rooms'),
    ('Arts Block', 'Studios, rehearsal rooms and art rooms'),
    ('Sports Complex', 'Indoor sports hall, changing rooms and the sports office'),
    ('Campus Gymnasium', 'Fitness centre and student gym'),
    ('Cafeteria', 'Main campus cafeteria and food court'),
    ('North Residence', 'Student accommodation in the north of the campus'),
    ('South Residence', 'Student accommodation in the south of the campus'),
    ('Parking Area', 'Staff and student parking areas'),
    ('Lost Property Office', 'Central office that holds unclaimed items'),
    ('Lecture Halls', 'Central lecture hall complex');
