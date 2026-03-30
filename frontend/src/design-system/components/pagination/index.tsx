import { Button } from '../button'

interface PaginationProps {
  canNext: boolean
  canPrevious: boolean
  currentPage: number
  onNext: () => void
  onPrevious: () => void
}

export function Pagination({ canNext, canPrevious, currentPage, onNext, onPrevious }: PaginationProps) {
  return (
    <div className="pagination">
      <Button disabled={!canPrevious} onClick={onPrevious} size="sm" variant="secondary">
        Previous
      </Button>
      <span className="muted-text">Page {currentPage + 1}</span>
      <Button disabled={!canNext} onClick={onNext} size="sm" variant="secondary">
        Next
      </Button>
    </div>
  )
}
