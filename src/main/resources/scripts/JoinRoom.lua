local maxPlayers = tonumber(redis.call('HGET', KEYS[2], 'maxPlayers'))
local currentState = redis.call('HGET', KEYS[2], 'state')

if not currentState then return 4 end -- 4: no room
if currentState ~= 'waiting' then return 0 end -- 0: game started

local currentPlayerCount = redis.call('SCARD', KEYS[1])
if currentPlayerCount >= maxPlayers then return 1 end -- 1: full

if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then return 2 end -- 2: already in

local added = redis.call('SADD', KEYS[1], ARGV[1])
if added == 1 then
    redis.call('HSET', KEYS[3], ARGV[1], ARGV[2]) -- set location
    redis.call('HSET', KEYS[4], ARGV[1], ARGV[3] .. ':'.. ARGV[4]) -- set endpoint
    return 3 -- 3:succeeded
else
    return 5 -- 5: player insertion failed (SADD failed)
end