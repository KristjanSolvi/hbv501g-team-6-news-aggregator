INSERT INTO news_sources (id, name, url) VALUES (RANDOM_UUID(), 'Example News', 'https://example.com');

INSERT INTO articles (id, title, category, published_at, url, source_id) VALUES
(RANDOM_UUID(), 'Demo: University opens a new study space', 'Education', '2026-09-15T12:00:00Z', 'https://example.com/study-space', (SELECT id FROM news_sources WHERE name = 'Example News')),
(RANDOM_UUID(), 'Demo: Local team wins the final', 'Sports', '2026-09-14T18:00:00Z', 'https://example.com/final', (SELECT id FROM news_sources WHERE name = 'Example News')),
(RANDOM_UUID(), 'Demo: Community science event announced', 'Science', '2026-09-13T09:00:00Z', 'https://example.com/science', (SELECT id FROM news_sources WHERE name = 'Example News'));
