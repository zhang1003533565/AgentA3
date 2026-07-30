import { useParams } from 'react-router-dom'
import FacilityPlaceManage from '../facility/FacilityPlaceManage/FacilityPlaceManage'

export default function StallManage() {
  const { canteenId } = useParams()

  return <FacilityPlaceManage sceneType="CANTEEN" rootPlaceId={canteenId} />
}
