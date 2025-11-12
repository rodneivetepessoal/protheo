import { loadStripe } from '@stripe/stripe-js'

let stripePromise = null

/**
 * Inicializa Stripe
 */
export function getStripe() {
  if (!stripePromise) {
    stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY)
  }
  return stripePromise
}

/**
 * Redireciona para Stripe Checkout
 */
export async function redirectToCheckout(sessionId) {
  const stripe = await getStripe()
  const { error } = await stripe.redirectToCheckout({ sessionId })
  
  if (error) {
    console.error('Stripe checkout error:', error)
    throw error
  }
}

/**
 * Cria sessão de pagamento
 * (Chamada ao backend que retorna session ID do Stripe)
 */
export async function createCheckoutSession(caseId, budgetId, labStripeAccountId) {
  // TODO: Implementar chamada ao backend
  // const { data } = await paymentsApi.create({ caseId, budgetId, labStripeAccountId })
  // return data.sessionId
  
  throw new Error('Not implemented')
}
