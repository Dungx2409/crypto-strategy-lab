ALTER TABLE news_items
    ALTER COLUMN input_version TYPE varchar(128);

ALTER TABLE sentiment_predictions
    ALTER COLUMN input_version TYPE varchar(128);
