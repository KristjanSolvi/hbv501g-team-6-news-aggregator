INSERT INTO news_sources (name, url) VALUES ('Example News', 'https://example.com');

INSERT INTO articles (title, category, published_at, url, source_id) VALUES
('Demo: University opens a new study space', 'Education', '2026-09-15T12:00:00Z', 'https://example.com/study-space', 1),
('Demo: Local team wins the final', 'Sports', '2026-09-14T18:00:00Z', 'https://example.com/final', 1),
('Demo: Community science event announced', 'Science', '2026-09-13T09:00:00Z', 'https://example.com/science', 1);
