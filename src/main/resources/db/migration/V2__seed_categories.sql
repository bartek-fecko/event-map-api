-- Reference data, not demo data: these categories ship to every environment.
INSERT INTO category (id, slug, name, color, icon)
VALUES ('8f1a5f52-1f0e-4a1c-9a43-000000000001', 'music', 'Music', '#e4572e', 'music'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000002', 'tech', 'Tech', '#2e86ab', 'code'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000003', 'sport', 'Sport', '#1b998b', 'run'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000004', 'art', 'Art', '#8e44ad', 'palette'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000005', 'food', 'Food & drink', '#f4a259', 'restaurant'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000006', 'theatre', 'Theatre & film', '#c1121f', 'theater'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000007', 'networking', 'Networking', '#457b9d', 'groups'),
       ('8f1a5f52-1f0e-4a1c-9a43-000000000008', 'nightlife', 'Nightlife', '#6a4c93', 'nightlife')
ON CONFLICT (slug) DO NOTHING;
