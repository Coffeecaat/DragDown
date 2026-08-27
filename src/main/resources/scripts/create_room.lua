local username = ARGV[1]
local roomId = ARGV[2]
local roomName = ARGV[3]
local hostIp = ARGV[4]
local hostPort = ARGV[5]
local maxPlayers = ARGV[6]
local createdAt = ARGV[7]

local function keyType(key)
    local result = redis.call('TYPE', key)
    if type(result) == 'table' then
        return result['ok']
    end
    return result
end

local detailsType = keyType(KEYS[1])
local playersType = keyType(KEYS[2])
local activeRoomsType = keyType(KEYS[3])
local playerLocationsType = keyType(KEYS[4])
local playerEndpointsType = keyType(KEYS[5])

if detailsType ~= 'none' and detailsType ~= 'hash' then
    return 3 -- 3: invalid Redis key type
end

if playersType ~= 'none' and playersType ~= 'set' then
    return 3
end

if activeRoomsType ~= 'none' and activeRoomsType ~= 'set' then
    return 3
end

if playerLocationsType ~= 'none' and playerLocationsType ~= 'hash' then
    return 3
end

if playerEndpointsType ~= 'none' and playerEndpointsType ~= 'hash' then
    return 3
end

if detailsType ~= 'none' or playersType ~= 'none' then
    return 2 -- 2: room ID collision, including orphan room keys
end

if redis.call('HGET', KEYS[4], username) then
    return 1 -- 1: user already belongs to a room
end

redis.call('HSET', KEYS[1],
        'name', roomName,
        'hostUsername', username,
        'hostIp', hostIp,
        'maxPlayers', maxPlayers,
        'state', 'waiting',
        'createdAt', createdAt)
redis.call('SADD', KEYS[2], username)
redis.call('SADD', KEYS[3], roomId)
redis.call('HSET', KEYS[4], username, roomId)
redis.call('HSET', KEYS[5], username, hostIp .. ':' .. hostPort)

return 0 -- 0: room created
