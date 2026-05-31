use feiapi;

update user
set user_name = '管理员'
where user_account = 'admin';

update interface_info
set description = '随机获取一条土味情话'
where name = 'getLoveWords'
  and method = 'GET';

update interface_info
set description = '根据用户对象获取用户名'
where name = 'getUsernameByPost'
  and method = 'POST';
