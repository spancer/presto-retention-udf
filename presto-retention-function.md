##  事件查询：
 直接利用union操作就行。ES的开始时间为T-1，T为日期（到天）； 举例查询

**SQL 语句**

```
SELECT 
  COUNT(DISTINCT user_id) 
FROM
  (SELECT 
    hpv.* 
  FROM
    default.page_visits hpv 
  WHERE hpv.event_id = 10008 
    AND hpv.event_date >= '20200628' 
    AND hpv.event_date <= '20200703' 
  UNION
  SELECT 
    epv.* 
  FROM
    default.page_visits epv 
  WHERE epv.event_id = 10008 
    AND epv.event_time >= 1593792000000)
    
## 1593792000000 为：2020-07-04 00:00:00

```

## 留存(UDAF函数)

该函数的输入数据是按用户分组的事件信息。


**语法**

```
retention(event_time, date, windowSize);
```

**参数**

- `event_time` — 事件发生的时间，bigint类型，精确到毫秒
- `date` —指定的查询日期，integer类型，如20200630
- `windowSize` —窗口时间，integer类型，如7日留存，则为7

**返回值**

返回值为：array(double) 。数组中的元素为1.0或0.0，分别表示满足或不满足。

- 1.0 — 条件满足。
- 0.0 — 条件不满足。

类型: `double`.

**示例**

以用户行为分析场景来说明 `retention` 的使用。

**1.** 举例说明，先创建一张Hive表。

```
create table if not exists page_visits (
      user_id varchar(64),
      event_time bigint,
      event_id int,
      event_name varchar(128),
      event_properties varchar(1024)
) partitioned by (`event_date` varchar(32)) STORED AS ORC;


```

输入表:

查询:

```
SELECT event_date AS date,user_id AS uid FROM page_visits
```

结果:

```
┌───────date─┬─uid─┐
│ 20200101 │   0 │
│ 20200101 │   1 │
│ 20200101 │   2 │
│ 20200101 │   3 │
│ 20200101 │   4 │
└────────────┴─────┘
┌───────date─┬─uid─┐
│ 20200102 │   0 │
│ 20200102 │   1 │
│ 20200102 │   2 │
│ 20200102 │   3 │
│ 20200102 │   4 │
│ 20200102 │   5 │
│ 20200102 │   6 │
│ 20200102 │   7 │
│ 20200102 │   8 │
│ 20200102 │   9 │
└────────────┴─────┘
┌───────date─┬─uid─┐
│ 20200103 │   0 │
│ 20200103 │   1 │
│ 20200103 │   2 │
│ 20200103 │   3 │
│ 20200103 │   4 │
│ 20200103 │   5 │
│ 20200103 │   6 │
│ 20200103 │   7 │
│ 20200103 │   8 │
│ 20200103 │   9 │
│ 20200103 │  10 │
│ 20200103 │  11 │
│ 20200103 │  12 │
│ 20200103 │  13 │
│ 20200103 │  14 │
└────────────┴─────┘
```

**2.** 按唯一ID `user_id` 对用户进行分组，使用 `retention` 功能。

查询:

```
SELECT
    user_id AS uid,
    retention(event_time, 20200101,3) AS r
FROM page_visits
WHERE 
event_id =10001 ##登录事件ID,做为留存计算的发生事件
AND event_date IN ('20200101', '20200102', '20200103')
GROUP BY user_id
```

结果:

```
┌─uid─┬─r───────┐
│   0 │ [1.0,1.0,1.0] │
│   1 │ [1.0,1.0,1.0] │
│   2 │ [1.0,1.0,1.0] │
│   3 │ [1.0,1.0,1.0] │
│   4 │ [1.0,1.0,1.0] │
│   5 │ [0.0,0.0,0.0] │
│   6 │ [0.0,0.0,0.0] │
│   7 │ [0.0,0.0,0.0] │
│   8 │ [0.0,0.0,0.0] │
│   9 │ [0.0,0.0,0.0] │
│  10 │ [0.0,0.0,0.0] │
│  11 │ [0.0,0.0,0.0] │
│  12 │ [0.0,0.0,0.0] │
│  13 │ [0.0,0.0,0.0] │
│  14 │ [0.0,0.0,0.0] │
└─────┴─────────┘
```

**3.** 计算近3日每天的总留存：

查询:

```
SELECT
    sum(r[1]) AS r1,
    sum(r[2]) AS r2,
    sum(r[3]) AS r3
FROM
(
    SELECT
        user_id AS uid,
        retention(event_time, 20200101,3) AS r
    FROM page_visits
    WHERE 
        event_id =10001 ##登录事件ID,做为留存计算的发生事件
        AND event_date IN ('20200101', '20200102', '20200103')
    GROUP BY user_id
 )
```

结果:

```
┌─r1─┬─r2─┬─r3─┐
│  5 │  5 │  5 │
└────┴────┴────┘
```

条件:

- `r1`-20200101期间有登录行为的用户数量; 即第1日留存用户量。
- `r2`-20200102期间有登录行为的用户数量; 即第2日留存用户量。
- `r3`-20200103期间有登录行为的用户数量; 即第2日留存用户量。

** 测试场景SQL举例**

  场景：查询时间为20200629，留存判定事件为10008 的7日留存。
	
```
SELECT 
  SUM(r [ 1 ]) AS r1,
  SUM(r [ 2 ]) AS r2,
  SUM(r [ 3 ]) AS r3,
  SUM(r [ 4 ]) AS r4,
  SUM(r [ 5 ]) AS r5,
  SUM(r [ 6 ]) AS r6,
  SUM(r [ 7 ]) AS r7 
FROM
  (SELECT 
    e.user_id,
    retention (e.event_time, 20200629, 7) AS r 
  FROM
    (SELECT 
      hpv.* 
    FROM
      hive.default.page_visits hpv 
    WHERE hpv.event_id = 10008 
      AND hpv.event_date >= '2020-06-29' 
      AND hpv.event_date <= '2020-07-03' 
    UNION
    SELECT 
      epv.* 
    FROM
      elasticsearch.default.page_visits epv 
    WHERE epv.event_id = 10008 
      AND epv.event_time >= 1593792000928) e 
  GROUP BY e.user_id) 
```
```
 说明：上述语句中：1593792000928 代表时间： 2020-07-04 00:00:00
```
单数据源：

```
SELECT 
  SUM(r [ 1 ]) AS r1,
  SUM(r [ 2 ]) AS r2,
  SUM(r [ 3 ]) AS r3,
  SUM(r [ 4 ]) AS r4,
  SUM(r [ 5 ]) AS r5,
  SUM(r [ 6 ]) AS r6,
  SUM(r [ 7 ]) AS r7 
FROM
  (SELECT 
    e.user_id,
    retention (e.event_time, 20200629, 7) AS r 
  FROM
    default.page_visits e 
  WHERE e.event_id = 10008 
    AND e.event_date >= '20200629' 
    AND e.event_date <= '20200705' 
  GROUP BY e.user_id)
```
