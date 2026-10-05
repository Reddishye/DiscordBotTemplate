create table preset_preference (
  id integer not null auto_increment primary key,
  created_at timestamp null,
  updated_at timestamp null,
  version bigint not null,
  scope varchar(16) not null,
  subject_id bigint not null,
  preset_name varchar(64) not null,
  constraint uk_preset_scope_subject unique (scope, subject_id)
);

create table channel_panel (
  id integer not null auto_increment primary key,
  created_at timestamp null,
  updated_at timestamp null,
  version bigint not null,
  guild_id bigint not null,
  channel_id bigint not null,
  menu_id varchar(64) not null,
  message_id bigint not null,
  constraint uk_channel_panel unique (guild_id, channel_id, menu_id)
);
