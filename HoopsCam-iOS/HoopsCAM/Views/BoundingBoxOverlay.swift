import SwiftUI

struct BoundingBoxOverlay: View {
    let detections: [Detection]
    
    var body: some View {
        GeometryReader { geometry in
            ForEach(detections) { detection in
                let bbox = detection.bbox
                let rect = CGRect(
                    x: bbox.origin.x * geometry.size.width,
                    y: bbox.origin.y * geometry.size.height,
                    width: bbox.size.width * geometry.size.width,
                    height: bbox.size.height * geometry.size.height
                )
                
                // ClassId 1 = Active (Green), 0 = Inactive (Red)
                let color = detection.classId == 1 ? Color.green : Color.red
                
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .path(in: rect)
                        .stroke(color, lineWidth: 2)
                }
            }
        }
    }
}
