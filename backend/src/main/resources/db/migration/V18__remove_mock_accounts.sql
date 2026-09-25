-- Remove identities created by the former demo-account migration. New accounts must be real.
DELETE FROM users WHERE id IN ('user_demo', 'admin_demo', 'auditor_demo', 'platform_demo');
DELETE FROM accounts WHERE id IN ('acct_demo_user', 'acct_demo_admin', 'acct_demo_auditor', 'acct_demo_platform');