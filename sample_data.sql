CREATE TABLE Locations (
    Name VARCHAR(100) NOT NULL,
    Address VARCHAR(255) NOT NULL,
    City VARCHAR(100) NOT NULL,
    Country VARCHAR(100) NOT NULL,
    PostalCode VARCHAR(20) NOT NULL
);

alter table public.Locations
    owner to postgres;

INSERT INTO public.Locations (Name, Address, City, Country, PostalCode) VALUES
('Central Park', '59th to 110th St', 'New York', 'USA', '10022'),
('Eiffel Tower', 'Champ de Mars, 5 Avenue Anatole', 'Paris', 'France', '75007'),
('Colosseum', 'Piazza del Colosseo, 1', 'Rome', 'Italy', '00184'),
('Sydney Opera House', 'Bennelong Point', 'Sydney', 'Australia', '2000'),
('Great Wall of China', 'Huairou District', 'Beijing', 'China', '101405'),
('Christ the Redeemer', 'Parque Nacional da Tijuca', 'Rio de Janeiro', 'Brazil', '22290-245'),
('Taj Mahal', 'Dharmapuri, Forest Colony', 'Agra', 'India', '282001'),
('Machu Picchu', '08680', 'Cusco Region', 'Peru', '08680'),
('Statue of Liberty', 'Liberty Island', 'New York', 'USA', '10004'),
('Big Ben', 'Westminster', 'London', 'United Kingdom', 'SW1A 0AA'),
('Louvre Museum', 'Rue de Rivoli', 'Paris', 'France', '75001'),
('Times Square', 'Manhattan', 'New York', 'USA', '10036'),
('Empire State Building', '350 5th Ave', 'New York', 'USA', '10118'),
('Golden Gate Bridge', 'Golden Gate Bridge', 'San Francisco', 'USA', '94129'),
('Stonehenge', 'Amesbury', 'Wiltshire', 'United Kingdom', 'SP4 7DE'),
('Buckingham Palace', 'Westminster', 'London', 'United Kingdom', 'SW1A 1AA'),
('Niagara Falls', 'Niagara Parkway', 'Ontario', 'Canada', 'L2G 3Y9'),
('Mount Fuji', 'Kitayama', 'Fujinomiya', 'Japan', '418-0112'),
('Burj Khalifa', '1 Sheikh Mohammed bin Rashid Blvd', 'Dubai', 'UAE', '00000'),
('Santorini', 'Santorini', 'Cyclades', 'Greece', '84700');