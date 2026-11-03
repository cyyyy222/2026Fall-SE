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
    email: '邮箱',
    phone: '手机号',
    role: '账号身份',
    verificationId: '验证码凭据',
    verificationCode: '验证码',
    currentPhone: '当前手机号',
    newPhone: '新手机号',
    newPassword: '新密码',
    account: '用户名',
    statement: '申诉陈述',
    pastBehavior: '以往行为信息',
  };
  if (!result.errors?.length) return result.message;
  return result.errors
    .map((e) => {
      const field = e.field.split('.').at(-1)!;
      const name = names[field] || e.field;
      if (e.code === 'NotNull' || e.code === 'NotBlank') return `请填写${name}`;
      if (['password', 'newPassword'].includes(field)) return `${name}须为8～72位字母和数字组合`;
      if (field === 'email') return '邮箱格式不正确，例如12345@qq.com；也可以留空';
      if (field === 'verificationCode') return '验证码必须是6位数字';
      if (field === 'verificationId') return '请先获取验证码';
      if (['phone', 'currentPhone', 'newPhone'].includes(field))
        return `${name}必须为1开头的11位数字`;
      if (['statement', 'pastBehavior'].includes(field)) return `${name}请填写20～2000字`;
      return `${name}：${e.message}`;
    })
    .join('；');
}
