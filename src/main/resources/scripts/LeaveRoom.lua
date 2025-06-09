local username = ARGV[1]
local roomId = ARGV[2]

-- delivering key names as KEYS array, generated from combination of Java's constant and roomId, can increase
-- the reusability of scripts.

local roomPlayersKey = KEYS[3] -- delivering result of getRoomPlayersKey(roomId) from MatchRoomRepository
local roomDetailsKey = KEYS[4] -- delivering result of getRoomDetailsKey(roomId) from MatchRoomRepository

if redis.call('EXISTS', roomDetailsKey) == 0 then -- 3: no room
    redis.call('HDEL', KEYS[1], username) -- location delete
    redis.call('HDEL', KEYS[2], username) -- IP delete (player:ips)
    return 3
end

redis.call('HDEL',KEYS[1],username)
redis.call('HDEL',KEYS[2],username)

local removed = redis.call('SREM', roomPlayersKey, username)
if removed == 0 then return 2 end -- 2: not on the list

local hostUsername = redis.call('HGET', roomDetailsKey, 'hostUsername')
local remainingCount = redis.call('SCARD', roomPlayersKey)

if username == hostUsername or remainingCount == 0 then
    redis.call('DEL', roomDetailsKey)
    redis.call('DEL', roomPlayersKey)
    redis.call('SREM', KEYS[5], roomId) -- active_set key(ROOMS_ACTIVE_SET_KEY)
    return 1 -- 1: room closed
else
    return 0 -- 0: ordinary exit
end