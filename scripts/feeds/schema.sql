CREATE TABLE IF NOT EXISTS files (
	id integer PRIMARY KEY,
	sha256 text NOT NULL,
	description text
);

CREATE TABLE IF NOT EXISTS feeds (
	 id integer PRIMARY KEY,
	 filename text NOT NULL,
	 sha256 text NOT NULL,
	 description text
);