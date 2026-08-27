local username = ARGV[1]
local roomId = ARGV[2]

-- delivering key names as KEYS array, generated from combination of Java's constant and roomId, can increase
-- the reusability of scripts.

local roomPlayersKey = KEYS[3] -- delivering result of getRoomPlayersKey(roomId) from MatchRoomRepository
local roomDetailsKey = KEYS[4] -- delivering result of getRoomDetailsKey(roomId) from MatchRoomRepository
local currentRoomId = redis.call('HGET', KEYS[1], username)

if currentRoomId and currentRoomId ~= roomId then
    return 4 -- 4: stale leave request; player already belongs to another room
end

if redis.call('EXISTS', roomDetailsKey) == 0 then -- 3: no room
    if currentRoomId == roomId then
        redis.call('HDEL', KEYS[1], username) -- location delete
        redis.call('HDEL', KEYS[2], username) -- IP delete (player:ips)
    end
    return 3
end

if currentRoomId == roomId then
    redis.call('HDEL', KEYS[1], username) -- location delete
    redis.call('HDEL', KEYS[2], username) -- IP delete (player:ips)
end

local removed = redis.call('SREM', roomPlayersKey, username)
if removed == 0 then return 2 end -- 2: not on the list

local hostUsername = redis.call('HGET', roomDetailsKey, 'hostUsername')
local remainingCount = redis.call('SCARD', roomPlayersKey)

if username == hostUsername or remainingCount == 0 then
    local remainingPlayers = redis.call('SMEMBERS', roomPlayersKey)
    for _, player in ipairs(remainingPlayers) do
        local playerRoomId = redis.call('HGET', KEYS[1], player)
        if playerRoomId == roomId then
            redis.call('HDEL', KEYS[1], player)
            redis.call('HDEL', KEYS[2], player)
        end
    end

    redis.call('DEL', roomDetailsKey)
    redis.call('DEL', roomPlayersKey)
    redis.call('SREM', KEYS[5], roomId) -- active_set key(ROOMS_ACTIVE_SET_KEY)
    return 1 -- 1: room closed
else
    return 0 -- 0: ordinary exit
end
