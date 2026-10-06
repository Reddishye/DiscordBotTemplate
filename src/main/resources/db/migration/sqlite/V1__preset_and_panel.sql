-- Preset last chosen for a guild or a user.
-- scope is GUILD or USER.
-- subject_id is that guild id or user id.
-- preset_name is the preset id, for example default.
create table preset_preference (
  id integer primary key autoincrement,
  created_at timestamp,
  updated_at timestamp,
  version bigint not null,
  scope varchar(16) not null,
  subject_id bigint not null,
  preset_name varchar(64) not null,
  constraint uk_preset_scope_subject unique (scope, subject_id)
);

-- Discord message currently showing one menu in a channel.
-- One row per guild, channel and menu.
-- message_id is the message the next publish edits.
create table channel_panel (
  id integer primary key autoincrement,
  created_at timestamp,
  updated_at timestamp,
  version bigint not null,
  guild_id bigint not null,
  channel_id bigint not null,
  menu_id varchar(64) not null,
  message_id bigint not null,
  constraint uk_channel_panel unique (guild_id, channel_id, menu_id)
);
