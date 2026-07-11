ALTER TABLE trip_versions
    ADD COLUMN budget_amount DECIMAL(14,2) NOT NULL AFTER estimated_total;
