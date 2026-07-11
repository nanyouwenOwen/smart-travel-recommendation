export interface Meta { requestId: string; nextCursor?: string; hasMore?: boolean }
export interface Envelope<T> { data: T; meta: Meta }
export interface ErrorDetail { field?: string; reason?: string }
export interface ApiErrorBody { error: { code: string; message: string; details?: ErrorDetail[] }; meta?: Meta }
export interface AuthTokens { accessToken: string; refreshToken: string; expiresIn: number; tokenType: 'Bearer' }
export interface UserProfile { id: string; email: string; displayName: string; createdAt: string }
export interface Money { amount: string; currency: string }
export type TripStatus = 'DRAFT' | 'GENERATING' | 'READY' | 'FAILED'
export type Category = 'TRANSPORTATION' | 'ACCOMMODATION' | 'FOOD' | 'ATTRACTION' | 'SHOPPING' | 'OTHER'
export interface TripSummary { id: string; destination: string; startDate: string; days: number; status: TripStatus; currentVersion?: number; failureCode?: string; createdAt: string }
export interface Activity { sequenceNumber: number; startTime: string; endTime: string; title: string; location: string; description?: string; estimatedCost: Money; transportAdvice?: string; category: Category }
export interface ItineraryDay { dayNumber: number; date: string; summary?: string; activities: Activity[] }
export interface BudgetBreakdown { categories: { category: Category; amount: Money }[]; total: Money; exceedsBudget: boolean; exceededBy?: Money }
export type DataFreshness = 'FRESH' | 'STALE' | 'UNAVAILABLE'
export interface DataSourceReference { provider: string; label: string; sourceUrl: string; license?: string; retrievedAt: string; sourceUpdatedAt?: string; freshness: DataFreshness }
export interface LocationReference { id: string; name: string; displayName: string; countryCode?: string; latitude: number; longitude: number; timezone: string; type?: string; sources: DataSourceReference[] }
export interface LocationSearchResult extends Omit<LocationReference, 'id'> { locationId: string }
export interface Trip extends TripSummary { budget: Money; estimatedTotal?: Money; currency?: string; travelers: number; preferences: string[]; timezone: string; additionalRequirements?: string; warnings?: string[]; itinerary: ItineraryDay[]; budgetBreakdown?: BudgetBreakdown; destinationLocation?: LocationReference }
export interface TripVersionSummary { versionNumber: number; estimatedTotal: Money; createdAt: string }
export interface TripVersion extends TripVersionSummary { warnings?: string[]; itinerary: ItineraryDay[]; budgetBreakdown: BudgetBreakdown }
export interface CreateTripInput { destination: string; destinationLocationId?: string; startDate: string; days: number; budget: Money; travelers: number; preferences: string[]; timezone: string; additionalRequirements?: string }
export interface WeatherDay { date: string; weatherCode: number; condition?: string; temperatureMax: number; temperatureMin: number; precipitationProbability?: number; precipitationAmount?: number; windSpeedMax?: number; sunrise?: string; sunset?: string }
export interface WeatherSnapshot { timezone: string; days: WeatherDay[]; unavailableDates: string[]; sources: DataSourceReference[]; freshness: DataFreshness; warning?: string }
export interface NearbyPlace { providerId: string; name: string; category: string; latitude: number; longitude: number; distanceMeters?: number; openingHours?: string; website?: string; providerUrl?: string; sourceUpdatedAt?: string }
export interface PlaceSnapshot { places: NearbyPlace[]; sources: DataSourceReference[]; freshness: DataFreshness; warning?: string }
export type MessageStatus = 'PENDING' | 'STREAMING' | 'COMPLETED' | 'FAILED'
export interface ConversationMessage { id: string; role: 'USER' | 'ASSISTANT'; content: string; status: MessageStatus; tripVersionNumber?: number; model?: string; usage?: { inputTokens?: number; outputTokens?: number }; errorCode?: string; createdAt: string; completedAt?: string; sources?: DataSourceReference[]; dataUpdatedAt?: string; freshness?: DataFreshness }
export interface Conversation { id: string; title?: string; tripId?: string; createdAt: string; updatedAt: string; messages: ConversationMessage[] }
export interface ConversationSummary { id: string; title?: string; tripId?: string; updatedAt: string }
export interface StreamAck { streamId: string; userMessageId: string; assistantMessageId: string; eventId: number }
export interface StreamDelta { streamId: string; messageId: string; sequence: number; content: string }
export interface StreamDone { streamId: string; messageId: string; status: 'COMPLETED'; usage: Record<string, number>; replayed: boolean }
export interface StreamFailure { streamId: string; code: string; message: string; retryable: boolean; final: boolean }
export type StreamEvent = { id?: number; type: 'ack'; data: StreamAck } | { id?: number; type: 'delta'; data: StreamDelta } | { id?: number; type: 'done'; data: StreamDone } | { id?: number; type: 'error'; data: StreamFailure }
