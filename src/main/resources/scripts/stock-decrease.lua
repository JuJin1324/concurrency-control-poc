local stockKey = KEYS[1]
local decreaseAmount = tonumber(ARGV[1])

-- 1. 재고 조회
local currentStock = redis.call('GET', stockKey)

-- 2. 재고 존재 여부 확인
if currentStock == false then
    return -1 -- Stock not found
end

-- 3. 재고 부족 확인
if tonumber(currentStock) < decreaseAmount then
    return -2 -- Insufficient stock
end

-- 4. 재고 차감
local remainingStock = redis.call('DECRBY', stockKey, decreaseAmount)
return remainingStock