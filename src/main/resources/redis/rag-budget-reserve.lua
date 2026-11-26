local id = ARGV[1]
local amount = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local remaining = tonumber(redis.call('HGET', KEYS[1], 'remainingMicroKrw')) or limit

-- REST 응답을 받지 못하고 같은 예약 ID로 다시 요청해도 한 번만 차감한다.
if redis.call('HEXISTS', KEYS[1], id) == 1 then
    return remaining
end
if remaining < amount then
    return -1
end

local nextRemaining = remaining - amount
redis.call('HSET', KEYS[1], 'remainingMicroKrw', nextRemaining, id, amount)
redis.call('EXPIREAT', KEYS[1], ARGV[4])
return nextRemaining
