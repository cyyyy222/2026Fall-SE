export type Role = 'PARTICIPANT' | 'ORGANIZER' | 'CHECKER' | 'ADMIN';
export type PhoneCodePurpose =
  'LOGIN' | 'REGISTER' | 'RESET_PASSWORD' | 'RECOVERY_CONTACT' | 'CHANGE_PHONE';
export interface PhoneProof {
  verificationId: string;
  verificationCode: string;
}
export type EventStatus =
  'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'CANCELLED' | 'TAKEN_DOWN' | 'ENDED';
export type OrderStatus =
  | 'WAITLISTED'
  | 'OFFERED'
  | 'UNPAID'
  | 'REGISTERED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED'
  | 'ABSENT';
export type TicketStatus = 'VALID' | 'CHECKED' | 'INVALID';
export interface User {
  id: string;
  username: string;
  role: Role;
  email: string | null;
  maskedPhone: string | null;
  displayName: string | null;
  mustChangePassword: boolean;
  sessionExpiresAt: string;
  restrictions: { kind: string; endsAt: string; reason: string }[];
  checkerSessionId: string | null;
}
export interface TicketType {
  id: string;
  name: string;
  capacity: number;
  priceJiao: number;
  saleStopped: boolean;
  occupied: number;
  remaining: number;
  salesStatus: string;
}
export interface Session {
  id: string;
  eventId: string;
  eventName: string;
  categoryId: string;
  categoryNameSnapshot: string;
  name: string;
  startsAt: string;
  endsAt: string;
  location: string;
  capacity: number;
  salesOpensAt: string;
  registrationClosesAt: string;
  remaining: number;
  sessionSalesStatus: string;
  ticketTypes: TicketType[];
  posterUrl: string | null;
  matchedTicketTypeIds?: string[];
}
export interface CampusEvent {
  id: string;
  name: string;
  categoryId: string;
  categoryNameSnapshot: string;
  proposedCategoryName?: string | null;
  description: string;
  posterUrl: string | null;
  sessions: Session[];
  status?: EventStatus;
  organizerId?: string;
  version?: number;
  submittedAt?: string | null;
  publishedAt?: string | null;
  terminationReason?: string | null;
  lastReviewReason?: string | null;
  settlementStatus?: string;
  expiresAt?: string | null;
}
export interface Order {
  id: string;
  requestId: string;
  status: OrderStatus;
  statusReason: string | null;
  createdAt: string;
  attendeeNameSnapshot: string;
  amountJiao: number;
  event: {
    eventId: string;
    eventName: string;
    categoryNameSnapshot: string;
    eventStatus: EventStatus;
    sessionId: string;
    sessionName: string;
    startsAt: string;
    endsAt: string;
    location: string;
    terminationReason: string | null;
    terminatedAt: string | null;
  };
  ticketTypeId: string;
  ticketTypeName: string;
  payDeadline: string | null;
  confirmDeadline: string | null;
  queuePosition: number | null;
  consecutivePaymentFailures: number;
  ticketId: string | null;
  activeRefundRequestId: string | null;
  allowedActions: string[];
}
export interface Ticket {
  id: string;
  orderId: string;
  status: TicketStatus;
  attendeeName: string;
  event: Order['event'];
  ticketTypeName: string;
  paidAmountJiao: number;
  checkedAt: string | null;
  usable: boolean;
  invalidReason: string | null;
}
export interface Message {
  id: string;
  type: string;
  title: string;
  body: string;
  createdAt: string;
  expiresAt: string;
  readAt: string | null;
  target: { resourceType: string; resourceId: string } | null;
  actionDeadline: string | null;
}
export interface Report {
  id: string;
  sessionId: string;
  eventId: string;
  reason: string;
  description: string;
  evidenceText: string;
  status: string;
  createdAt: string;
  conclusion: string | null;
  resolvedAt: string | null;
  reporterId?: string;
}
export interface Category {
  id: string;
  name: string;
  enabled: boolean;
  version: number;
}
export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}
export interface Result<T> {
  code: string;
  message: string;
  data: T;
  traceId: string;
  serverTime: string;
  errors?: { field: string; code: string; message: string }[];
}
export interface Mutation<T> {
  operationId: string;
  operationResult: string;
  replayed: boolean;
  current: T;
}
export interface PaymentAttempt {
  id: string;
  attemptId: string;
  orderId: string;
  result: string;
  amountJiao: number;
  createdAt: string;
  failureReason: string | null;
}
export interface RefundRequest {
  id: string;
  orderId: string;
  source: string;
  status: string;
  reason: string;
  acceptedAt: string;
  initialAttemptId: string;
}
export interface RefundAttempt {
  id: string;
  requestId: string;
  orderId: string;
  result: string;
  amountJiao: number;
  createdAt: string;
}
export interface PaymentAudit {
  paymentAttempts: PaymentAttempt[];
  refundRequests: RefundRequest[];
  refundAttempts: RefundAttempt[];
}
export interface LiveCounts {
  eventId: string;
  sessionId: string | null;
  asOf: string;
  orderCounts: Record<string, number>;
  currentWaitlist: number;
  cumulativeWaitlistApplications: number;
  occupied: number;
  remaining: number;
  checkedIn: number;
  settlementStatus: string;
  ticketTypeCounts: {
    sessionId: string;
    ticketTypeId: string;
    capacity: number;
    occupied: number;
    remaining: number;
  }[];
}
export interface Statistics {
  eventId: string;
  settlementStatus: string;
  issuedCount: number | null;
  refundCount: number | null;
  checkedInCount: number | null;
  absentCount: number | null;
  attendanceRate: number | null;
  absenceRate: number | null;
  capacityUtilizationRate: number | null;
  expiresAt: string | null;
  cumulativeWaitlistApplications: number | null;
}
export interface Attendee {
  attendeeName: string;
  sessionId: string;
  sessionName: string;
  ticketTypeId: string;
  ticketTypeName: string;
  orderStatus: OrderStatus;
  checkinStatus: string;
  ticketId: string | null;
}
export interface ManagedAccount {
  id: string;
  username: string;
  role: Role;
  maskedPhone: string | null;
  enabled: boolean;
  deletedAt: string | null;
  mustChangePassword: boolean;
  assignedSessionId: string | null;
  eventId: string | null;
  version: number;
}
export interface Job {
  id: string;
  type: string;
  eventId: string | null;
  status: string;
  dueAt: string;
  deadlineAt: string;
  completedAt: string | null;
  total: number;
  processed: number;
  retryCount: number;
  overdue: boolean;
  lastError: string | null;
}
export interface AuditLog {
  id: string;
  actorId: string | null;
  actorRole: string;
  action: string;
  objectType: string;
  objectId: string | null;
  result: string;
  failureReason: string | null;
  createdAt: string;
  traceId: string;
}

export interface Restriction {
  id: string;
  kind: string;
  startsAt: string;
  endsAt: string;
  reason: string;
  source: string | null;
  active: boolean;
}
export interface RestrictionAppeal {
  id: string;
  restrictionId: string;
  userId: string;
  username: string;
  reason: string;
  evidenceText: string | null;
  status: string;
  createdAt: string;
  reviewedAt: string | null;
  decisionNote: string | null;
  restriction: Restriction;
}
