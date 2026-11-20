CREATE TABLE folders (id TEXT PRIMARY KEY, name TEXT NOT NULL, normalized_name TEXT NOT NULL UNIQUE);
CREATE TABLE notes (id TEXT PRIMARY KEY, title TEXT NOT NULL, folder_id TEXT REFERENCES folders(id) ON DELETE SET NULL, pinned INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL, revision INTEGER NOT NULL, is_draft INTEGER NOT NULL DEFAULT 1, deleted INTEGER NOT NULL DEFAULT 0, search_text TEXT NOT NULL, has_content INTEGER NOT NULL DEFAULT 0);
CREATE INDEX notes_order ON notes(deleted,pinned DESC,updated_at DESC,id);
CREATE TABLE note_blocks (id TEXT PRIMARY KEY, note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE, position INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, checked INTEGER NOT NULL, bold INTEGER NOT NULL, marks_json TEXT NOT NULL, UNIQUE(note_id,position));
CREATE TABLE tasks (id TEXT PRIMARY KEY, date TEXT NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL, completed INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, reminder_local TEXT, reminded_revision INTEGER NOT NULL DEFAULT -1);
CREATE INDEX tasks_date ON tasks(deleted,date,completed,id);
INSERT INTO folders(id,name,normalized_name) VALUES('study','Учёба','учёба');
INSERT INTO folders(id,name,normalized_name) VALUES('personal','Личное','личное');
INSERT INTO folders(id,name,normalized_name) VALUES('ideas','Идеи','идеи');

