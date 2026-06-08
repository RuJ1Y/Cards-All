-- 0) 账号表（登录/注册）
create table if not exists public.player_account (
  player_id text primary key,
  username text not null unique,
  password_hash text not null,
  created_at timestamptz not null default now()
);

-- 1) 房间状态快照（联机同步）
create table if not exists public.game_room_state (
  room_id text primary key,
  version integer not null default 1,
  state_json jsonb not null,
  last_action_by text not null,
  updated_at timestamptz not null default now()
);

create index if not exists idx_game_room_state_updated_at
  on public.game_room_state(updated_at desc);

-- 通用更新时间触发器
create or replace function public.set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_game_room_state_updated_at on public.game_room_state;
create trigger trg_game_room_state_updated_at
before update on public.game_room_state
for each row execute function public.set_updated_at();

-- 2) 玩家档案（头像、金币、当前选中角色）
create table if not exists public.player_profile (
  player_id text primary key,
  lottery_points integer not null default 0,
  avatar_base64 text,
  selected_character_1_id text,
  selected_character_2_id text,
  updated_at timestamptz not null default now()
);

-- 兼容已存在旧表（缺字段时补齐）
alter table public.player_profile add column if not exists avatar_base64 text;
alter table public.player_profile add column if not exists selected_character_1_id text;
alter table public.player_profile add column if not exists selected_character_2_id text;

drop trigger if exists trg_player_profile_updated_at on public.player_profile;
create trigger trg_player_profile_updated_at
before update on public.player_profile
for each row execute function public.set_updated_at();

-- 3) 玩家拥有角色（长期数据）
create table if not exists public.player_characters (
  player_id text not null,
  character_id text not null,
  owned_at timestamptz not null default now(),
  primary key (player_id, character_id)
);

-- 4) 对战结果（可选，统计用途）
create table if not exists public.game_result (
  id bigserial primary key,
  room_id text not null,
  player_a_id text not null,
  player_b_id text not null,
  winner_player_id text not null,
  created_at timestamptz not null default now()
);

-- 5) RLS（当前仍使用 anon key 直连，先不开启）
-- 你后续若切换 Supabase Auth，再按 auth.uid() 补策略并启用 RLS。
