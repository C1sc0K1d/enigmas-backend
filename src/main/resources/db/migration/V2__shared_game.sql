-- Keep previous private sessions intact; all new joins use the shared campaign.
INSERT INTO game_sessions (id, epoch, snapshot, revision)
VALUES ('00000000-0000-4000-8000-000000000001', 'initial', '{}', 0);