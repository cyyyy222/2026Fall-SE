export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status = 409,
    public context: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function apiErrorMessage(result: {
  message: string;
  errors?: { field: string; code: string; message: string }[];
}) {
  const names: Record<string, string> = {
    username: '用户名',
    password: '密码',
    oldPassword: '原密码',
    newPassword: '新密码',
    confirmPassword: '确认密码',
    initialPassword: '初始密码',
    email: '邮箱',
    phone: '手机号',
    currentPhone: '当前手机号',
    newPhone: '新手机号',
    oldPhone: '原手机号',
    role: '账号身份',
    verificationId: '验证码凭据',
    verificationCode: '验证码',
    displayName: '本人姓名',
    account: '用户名',
    statement: '申诉陈述',
    pastBehavior: '以往行为信息',
    eventId: '活动',
    sessionId: '场次',
    ticketTypeId: '票档',
    orderId: '订单',
    ticketId: '电子票',
    name: '名称',
    description: '简介',
    location: '地点',
    capacity: '容量',
    priceJiao: '价格',
    startsAt: '开始时间',
    endsAt: '结束时间',
    salesOpensAt: '放票时间',
    registrationClosesAt: '报名截止时间',
    categoryId: '活动类别',
    proposedCategoryName: '新类别名称',
    sessions: '场次',
    ticketTypes: '票种',
    reason: '原因',
    evidenceText: '证据说明',
    conclusion: '结论',
    restrictionId: '限制记录',
    decision: '审核结论',
    note: '审核说明',
    expectedVersion: '数据版本',
    objectType: '对象类型',
    objectId: '对象编号',
  };
  const labelOf = (field: string) => {
    const last = field.split('.').at(-1) || field;
    const base = last.replace(/\[\d+\]/g, '');
    const label = names[base] || names[base.toLowerCase()] || base;
    const prefixes: string[] = [];
    const session = field.match(/sessions\[(\d+)\]/);
    const ticketType = field.match(/ticketTypes\[(\d+)\]/);
    if (session) prefixes.push(`第${Number(session[1]) + 1}个场次`);
    if (ticketType) prefixes.push(`第${Number(ticketType[1]) + 1}个票种`);
    return prefixes.length ? `${prefixes.join('')}的${label}` : label;
  };
  if (!result.errors?.length) return result.message;
  return result.errors
    .map((e) => {
      const field = e.field.split('.').at(-1) || e.field;
      const base = field.replace(/\[\d+\]/g, '');
      const name = labelOf(e.field);
      if (e.code === 'NotNull' || e.code === 'NotBlank' || e.code === 'NotEmpty')
        return `请填写${name}`;
      if (['password', 'newPassword', 'initialPassword', 'confirmPassword'].includes(base))
        return `${name}须为8～72位字母和数字组合`;
      if (base === 'email') return '邮箱格式不正确，例如12345@qq.com；也可以留空';
      if (base === 'verificationCode') return `${name}必须是 6 位数字`;
      if (base === 'verificationId') return '请先获取验证码';
      if (['phone', 'currentPhone', 'newPhone', 'oldPhone'].includes(base))
        return `${name}必须为 1 开头的 11 位数字`;
      if (['statement', 'pastBehavior'].includes(base)) return `${name}请填写 20～2000 字`;
      if (['startsAt', 'endsAt', 'salesOpensAt', 'registrationClosesAt'].includes(base))
        return `${name}格式不正确：请选择完整日期和时间（精确到分钟）`;
      if (e.code === 'Size') return `${name}长度不符合要求`;
      if (['Min', 'Max', 'DecimalMin', 'DecimalMax', 'Positive', 'PositiveOrZero'].includes(e.code))
        return `${name}超出允许范围`;
      if (e.code === 'Email') return `${name}格式不正确，例如 12345@qq.com`;
      if (e.code === 'Pattern' || e.code === 'InvalidFormat')
        return `${name}格式不正确，请按页面提示填写`;
      if (e.code === 'BeijingTime') return `${name}格式不正确：请使用带时区的日期时间，秒为 0`;
      return `${name}：${e.message}`;
    })
    .join('；');
}
