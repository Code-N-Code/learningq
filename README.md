# Database migrations

- Use filenames such as V1__create_users.sql.
- Separate the version and description with two underscores.
- Give each versioned migration a unique, increasing version.
- Never edit a migration already applied to a shared database.
- Make subsequent schema changes through new migrations.
- Write SQL compatible with MariaDB 11.8