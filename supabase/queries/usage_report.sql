-- Manabind — reading the anonymous usage counts (public.usage_counts, from
-- migrations/20261006030000_usage_counts.sql). Not a migration: run a query at a time from the
-- dashboard's SQL editor or `npx supabase db query --linked "<query>"`.
--
-- An "install" is an anonymous id that changes every 90 days, so a person who keeps the app for
-- longer shows up as more than one install over a long window. Within 30 days it's close enough.

-- 1. Daily active installs per platform, last 30 days.
select day, platform, count(distinct install_id) as installs
from public.usage_counts
where day >= current_date - 30
group by day, platform
order by day desc, platform;

-- 2. Top screens, last 30 days: times opened, and by how many installs.
select substr(event, 8) as screen, platform, sum(count) as opens, count(distinct install_id) as installs
from public.usage_counts
where day >= current_date - 30 and event like 'screen\_%'
group by screen, platform
order by opens desc
limit 50;

-- 3. Top actions, last 30 days.
select event as action, platform, sum(count) as times, count(distinct install_id) as installs
from public.usage_counts
where day >= current_date - 30 and event not like 'screen\_%'
group by event, platform
order by times desc;

-- 4. Feature adoption, last 30 days: of the installs active at all, the share that used each
--    screen or action at least once.
with active as (
  select platform, count(distinct install_id) as installs
  from public.usage_counts
  where day >= current_date - 30
  group by platform
), used as (
  select platform, event, count(distinct install_id) as installs
  from public.usage_counts
  where day >= current_date - 30
  group by platform, event
)
select used.event, used.platform, used.installs, active.installs as active_installs,
       round(100.0 * used.installs / nullif(active.installs, 0), 1) as percent
from used join active using (platform)
order by used.platform, percent desc;

-- 4b. One feature, both platforms together: installs that used it at least once in 30 days.
--     Change 'deck_created' to the event to check.
select count(distinct install_id) filter (where event = 'deck_created') as used_it,
       count(distinct install_id) as active_installs
from public.usage_counts
where day >= current_date - 30;

-- 5. Versions seen, last 7 days.
select platform, app_version, count(distinct install_id) as installs
from public.usage_counts
where day >= current_date - 7
group by platform, app_version
order by platform, installs desc;
