export function StatusMessage({
  error,
  message,
}: {
  error: string
  message: string
}) {
  if (!error && !message) {
    return null
  }

  return <p className={error ? 'status error' : 'status success'}>{error || message}</p>
}
