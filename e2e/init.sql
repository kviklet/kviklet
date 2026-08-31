create table public.test
(
    id   integer,
    test varchar
);

alter table public.test
    owner to postgres;

INSERT INTO public.test (id, test) VALUES (2, 'blu');
INSERT INTO public.test (id, test) VALUES (54, 'bli');
INSERT INTO public.test (id, test) VALUES (32, 'cookies');
INSERT INTO public.test (id, test) VALUES (11, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (22, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (111, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (222, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (333, 'sample_text3');
INSERT INTO public.test (id, test) VALUES (1, 'some new value');
INSERT INTO public.test (id, test) VALUES (111, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (222, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (333, 'sample_text3');
INSERT INTO public.test (id, test) VALUES (2, 'blu');
INSERT INTO public.test (id, test) VALUES (54, 'bli');
INSERT INTO public.test (id, test) VALUES (32, 'cookies');
INSERT INTO public.test (id, test) VALUES (11, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (22, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (111, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (222, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (333, 'sample_text3');
INSERT INTO public.test (id, test) VALUES (1, 'some new value');
INSERT INTO public.test (id, test) VALUES (111, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (222, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (333, 'sample_text3');
INSERT INTO public.test (id, test) VALUES (2, 'blu');
INSERT INTO public.test (id, test) VALUES (54, 'bli');
INSERT INTO public.test (id, test) VALUES (32, 'cookies');
INSERT INTO public.test (id, test) VALUES (11, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (22, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (111, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (222, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (333, 'sample_text3');
INSERT INTO public.test (id, test) VALUES (1, 'some new value');
INSERT INTO public.test (id, test) VALUES (111, 'sample_text1');
INSERT INTO public.test (id, test) VALUES (222, 'sample_text2');
INSERT INTO public.test (id, test) VALUES (333, 'sample_text3');
INSERT INTO public.test (id, test) VALUES (33, 'a new value');
INSERT INTO public.test (id, test) VALUES (33, 'a new value');
INSERT INTO public.test (id, test) VALUES (33, 'a new value');

-- Sample data for the automated README screenshots (screenshots.spec.ts):
-- the hero screenshot executes an UPDATE against this table.
create table public.shipping
(
    shipping_id     integer primary key,
    customer        varchar,
    tracking_number varchar,
    status          varchar
);

INSERT INTO public.shipping VALUES (11, 'Acme Corp', 'TRACK1024', 'delivered');
INSERT INTO public.shipping VALUES (12, 'Globex', 'TRACK1187', 'in_transit');
INSERT INTO public.shipping VALUES (13, 'Initech', 'TRACK1201', 'in_transit');
INSERT INTO public.shipping VALUES (14, 'Umbrella', 'TRACK1355', 'pending');
