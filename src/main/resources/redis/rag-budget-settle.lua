local remaining = tonumber(redis.call('HGET', KEYS[1], 'remainingMicroKrw'))
if not remaining then
    return -2
end

local reserved = tonumber(redis.call('HGET', KEYS[1], ARGV[1]))
local charged = tonumber(ARGV[2])
if not reserved then
    return -2
end
if reserved == -1 then
    return remaining
end
if charged > reserved or charged < 0 then
    return -2
end

local nextRemaining = remaining + reserved - charged
-- 정산 마커를 만료까지 보관해 중복 정산과 정산 뒤 예약 재전송을 방지한다.
redis.call('HSET', KEYS[1], 'remainingMicroKrw', nextRemaining, ARGV[1], -1)
return nextRemaining
